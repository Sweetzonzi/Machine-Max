package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadJobKey} 的排序契约。<br>
 * 实际架在强制损失后按这个全序排列预留键、再从末尾（键值最大者）开始撤销，因此顺序必须是
 * "宿主 SubPart ID 按整数 → 发射器名按 {@code String.compareTo} → 作业编号按整数"升序，
 * 不能依赖哈希顺序。
 */
class ReloadJobKeyTest {

    @Test
    void noneIsRecognisedByJobIdSoAnUnpublishedJobNeverCollides() {
        assertTrue(ReloadJobKey.NONE.isNone());
        assertFalse(new ReloadJobKey(0, "launcher", 1).isNone());
    }

    @Test
    void ordersBySubPartIdThenNameThenJobId() {
        List<ReloadJobKey> keys = new ArrayList<>(List.of(
                new ReloadJobKey(7, "b", 1),
                new ReloadJobKey(3, "z", 9),
                new ReloadJobKey(7, "a", 5),
                new ReloadJobKey(7, "a", 2),
                new ReloadJobKey(3, "a", 9)
        ));
        keys.sort(ReloadJobKey::compareTo);

        assertEquals(List.of(
                new ReloadJobKey(3, "a", 9),
                new ReloadJobKey(3, "z", 9),
                new ReloadJobKey(7, "a", 2),
                new ReloadJobKey(7, "a", 5),
                new ReloadJobKey(7, "b", 1)
        ), keys);
    }

    @Test
    void equalityCoversAllThreeFieldsSoOldAndNewJobsDoNotClaimEachOther() {
        ReloadJobKey job = new ReloadJobKey(3, "launcher", 1);
        assertEquals(job, new ReloadJobKey(3, "launcher", 1));
        assertFalse(job.equals(new ReloadJobKey(3, "launcher", 2)));
        assertFalse(job.equals(new ReloadJobKey(4, "launcher", 1)));
        assertFalse(job.equals(new ReloadJobKey(3, "other", 1)));
    }
}
