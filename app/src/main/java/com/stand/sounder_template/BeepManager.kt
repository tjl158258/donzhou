package com.stand.sounder_template

import android.content.Context
import android.content.res.AssetFileDescriptor

object BeepManager {
    private var audioNames: List<String>? = null
    // 核心控制变量
    private var nextTargetIndex = 0 // 下一个要提升概率的音频索引
    private var currentBoostProbability = 60 // 目标音频提升概率（初始60%）
    private var isProbBoosted = false // 标记是否进入“概率提升序列”

    /**
     * 重置所有概率状态到初始值
     */
    private fun resetProbState() {
        nextTargetIndex = 0
        currentBoostProbability = 60
        isProbBoosted = false
    }

    /**
     * 加载assets/audio目录下的所有音频文件名
     */
    private fun loadAssets(context: Context) {
        if (audioNames != null) return
        audioNames = context.assets.list("audio")?.toList()
        require(!audioNames.isNullOrEmpty()) { "assets/audio/ 目录为空，请检查音频文件是否存在" }
    }

    /**
     * 最终版随机音频逻辑（严格匹配所有需求）
     */
    fun randomAssetFd(context: Context): AssetFileDescriptor {
        loadAssets(context)
        val audioList = audioNames!!
        val totalAudioCount = audioList.size
        val probabilities = mutableMapOf<String, Int>()

        // 1. 分配概率（分两种核心场景）
        if (!isProbBoosted) {
            // 场景1：初始/重置状态，所有音频概率完全平分（处理不能整除的余数）
            val equalBaseProb = 100 / totalAudioCount
            val remainProb = 100 % totalAudioCount
            audioList.forEachIndexed { index, audio ->
                probabilities[audio] = if (index < remainProb) equalBaseProb + 1 else equalBaseProb
            }
        } else {
            // 场景2：概率提升序列中，目标音频高概率，其他音频平分剩余概率
            val targetAudio = audioList[nextTargetIndex]
            val targetProb = currentBoostProbability
            val remainingProb = 100 - targetProb
            val otherAudioCount = totalAudioCount - 1
            val otherAudioBaseProb = maxOf(1, remainingProb / otherAudioCount) // 其他音频至少1%，避免0概率

            // 分配概率并修正总和为100%（处理整除误差）
            audioList.forEach { audio ->
                probabilities[audio] = if (audio == targetAudio) targetProb else otherAudioBaseProb
            }
            val totalProb = probabilities.values.sum()
            if (totalProb != 100) {
                probabilities[targetAudio] = probabilities[targetAudio]!! + (100 - totalProb)
            }
        }

        // 2. 按权重随机选择音频，并获取其索引
        val selectedAudio = weightedRandom(audioList, probabilities)
        val selectedIndex = audioList.indexOf(selectedAudio)

        // 3. 核心状态更新（严格遵循需求规则）
        if (!isProbBoosted) {
            // 未进入提升序列时：仅抽中第一个音频，才启动序列
            if (selectedIndex == 0) {
                isProbBoosted = true
                nextTargetIndex = 1 // 下一个目标设为第二个音频
            }
            // 抽中除第一个外的任何音频（如2、3、4...），均保持初始状态，不提升后续概率
        } else {
            // 已进入提升序列时：判断是否抽中当前目标音频
            if (selectedIndex == nextTargetIndex) {
                // 抽中目标：判断是否为最后一个音频
                if (nextTargetIndex == totalAudioCount - 1) {
                    // 抽中最后一个，立即重置所有概率到初始状态
                    resetProbState()
                } else {
                    // 不是最后一个，更新下一个目标和提升概率（最多90%，避免剩余概率不足）
                    nextTargetIndex += 1
                    currentBoostProbability = minOf(90, currentBoostProbability + 10)
                }
            } else {
                // 未抽中当前目标，直接重置所有概率，需重新抽中第一个音频才启动序列
                resetProbState()
            }
        }

        // 4. 返回选中音频的文件描述符
        return context.assets.openFd("audio/$selectedAudio")
    }

    /**
     * 权重随机工具函数：根据概率分配结果，返回选中的音频文件名
     */
    private fun weightedRandom(audioList: List<String>, probMap: Map<String, Int>): String {
        val totalWeight = probMap.values.sum()
        var randomValue = (0 until totalWeight).random()

        for (audio in audioList) {
            val weight = probMap[audio] ?: 1
            if (randomValue < weight) {
                return audio
            }
            randomValue -= weight
        }
        // 兜底逻辑：理论上不会触发，防止极端情况
        return audioList.random()
    }
}
