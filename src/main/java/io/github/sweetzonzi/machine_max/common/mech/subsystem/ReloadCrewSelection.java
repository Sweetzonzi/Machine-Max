package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import java.util.List;
import java.util.UUID;

/**
 * 人工段选人的比较部分：给定候选的预计完成时间，挑出该由谁来装填。<br>
 * 单个候选的代价由调用方算好（剩余标准人力时间 × 岗位倍率 × 个人倍率，见装填计划 §4.3），这里只做两件事：
 * <ul>
 *   <li><b>取最小代价：</b>一个座位对应一个候选，一个乘员也只会在一个座位里出现；多条发现线路到达同一座位
 *       时，负责发现的 {@link LoaderRoster} 已经把该座位的岗位倍率收成一个值（取其中最低的那条），
 *       因此这里看到的就是"每个座位一条代价"</li>
 *   <li><b>稳定决胜：</b>代价相同时按座位（宿主 SubPart ID、座位名）与人物 UUID 的字典序决定，
 *       不依赖遍历顺序；"代价相同时保持当前执行者"由调用方在换人判断里处理</li>
 * </ul>
 * 放在这里而不是写进 {@link AmmoLoaderSubsystem} 的 tick 里，是因为它只依赖数值与身份，能用单元测试固定；
 * 装填机构负责把座位、乘员与倍率读成候选。
 */
final class ReloadCrewSelection {

    /**
     * 一个候选执行者：谁、坐在哪、干完还要多久。
     *
     * @param person        执行者身份
     * @param seatSubPartId 候选座位的宿主 SubPart ID（决胜键的第一项）
     * @param seatName      候选座位的子系统名（决胜键的第二项）
     * @param estimateTicks 预计完成剩余人工段所需的 tick
     */
    record Candidate(UUID person, int seatSubPartId, String seatName, double estimateTicks) {
    }

    private ReloadCrewSelection() {
    }

    /**
     * 选出该用的候选下标。
     *
     * @param candidates 候选，可为空
     * @return 最优候选的下标；没有候选时返回 -1
     */
    static int bestIndex(List<Candidate> candidates) {
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (best < 0 || isBetter(candidates.get(i), candidates.get(best))) best = i;
        }
        return best;
    }

    /**
     * 严格更优：先比预计完成时间，再按座位（SubPart ID → 座位名）与人物 UUID 的字典序决胜。
     */
    private static boolean isBetter(Candidate a, Candidate b) {
        int byEstimate = Double.compare(a.estimateTicks(), b.estimateTicks());
        if (byEstimate != 0) return byEstimate < 0;
        int bySubPart = Integer.compare(a.seatSubPartId(), b.seatSubPartId());
        if (bySubPart != 0) return bySubPart < 0;
        int bySeat = a.seatName().compareTo(b.seatName());
        if (bySeat != 0) return bySeat < 0;
        return a.person().compareTo(b.person()) < 0;
    }
}
