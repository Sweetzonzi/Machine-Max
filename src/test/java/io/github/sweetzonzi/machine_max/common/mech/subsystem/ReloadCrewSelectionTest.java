package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 人工段选人的比较契约：取预计完成时间最小者，代价相同时按座位与人物身份决胜。<br>
 * 同一个人经多条发现线路出现时，最小者就是它最有利的那条关系，倍率不相乘——这条不需要额外的去重分支，
 * 用"两条线路、一好一坏"的候选固定住即可。
 */
class ReloadCrewSelectionTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    private static ReloadCrewSelection.Candidate candidate(UUID person, int subPartId, String seat, double ticks) {
        return new ReloadCrewSelection.Candidate(person, subPartId, seat, ticks);
    }

    @Test
    void noCandidateMeansNoChoice() {
        assertEquals(-1, ReloadCrewSelection.bestIndex(List.of()));
    }

    @Test
    void theShortestEstimateWins() {
        List<ReloadCrewSelection.Candidate> candidates = List.of(
                candidate(ALICE, 2, "gunner_seat", 120),
                candidate(BOB, 3, "loader_seat", 80),
                candidate(ALICE, 2, "commander_seat", 200));
        assertEquals(1, ReloadCrewSelection.bestIndex(candidates));
    }

    @Test
    void onePersonIsJudgedByItsBestLine() {
        // 同一个人两条线路：装填手座位 60 tick、炮手座位 200 tick。系统只该按 60 这一条比较，
        // 于是这个人（60）赢过第三人（100），而不是被自己的坏线路拖成 200
        List<ReloadCrewSelection.Candidate> candidates = List.of(
                candidate(ALICE, 2, "loader_seat", 60),
                candidate(ALICE, 2, "gunner_seat", 200),
                candidate(BOB, 3, "commander_seat", 100));
        assertEquals(0, ReloadCrewSelection.bestIndex(candidates));
    }

    @Test
    void equalEstimatesTieBreakOnTheSeatIdentityNotOnOrder() {
        List<ReloadCrewSelection.Candidate> bySeatName = List.of(
                candidate(BOB, 4, "loader_seat", 100),
                candidate(ALICE, 4, "gunner_seat", 100));
        assertEquals(1, ReloadCrewSelection.bestIndex(bySeatName), "同 SubPart 时按座位名决胜");

        List<ReloadCrewSelection.Candidate> bySubPart = List.of(
                candidate(ALICE, 5, "loader_seat", 100),
                candidate(ALICE, 2, "loader_seat", 100));
        assertEquals(1, ReloadCrewSelection.bestIndex(bySubPart), "先按宿主 SubPart ID 决胜");

        List<ReloadCrewSelection.Candidate> byPerson = List.of(
                candidate(BOB, 2, "loader_seat", 100),
                candidate(ALICE, 2, "loader_seat", 100));
        int smallerUuid = ALICE.compareTo(BOB) < 0 ? 1 : 0;
        assertEquals(smallerUuid, ReloadCrewSelection.bestIndex(byPerson), "座位完全相同时按人物身份决胜");
    }

    @Test
    void theChoiceDoesNotDependOnIterationOrder() {
        List<ReloadCrewSelection.Candidate> forward = List.of(
                candidate(ALICE, 2, "loader_seat", 100),
                candidate(BOB, 3, "commander_seat", 100));
        List<ReloadCrewSelection.Candidate> reversed = List.of(
                candidate(BOB, 3, "commander_seat", 100),
                candidate(ALICE, 2, "loader_seat", 100));
        assertEquals(0, ReloadCrewSelection.bestIndex(forward));
        assertEquals(1, ReloadCrewSelection.bestIndex(reversed), "换一个遍历顺序仍然选同一个人");
    }
}
