package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 人工段选人的比较契约：取预计完成时间最小者，代价相同时按座位与人物身份决胜。<br>
 * 进来的是"每个座位一条代价"：多条发现线路到达同一座位时，负责发现的 {@link ReloadOperatorRoster} 已经把该
 * 座位收成一个岗位倍率，因此这里没有"同一个人多条线路取最有利"的分支，那条规则在发现侧，不在这层。
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
