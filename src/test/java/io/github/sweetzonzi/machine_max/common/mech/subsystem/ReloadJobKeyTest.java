package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReloadJobKey} 的排序与身份契约。<br>
 * 实际架在强制损失后按这个全序排列预留键、再从末尾（键值最大者）开始撤销，因此顺序必须是
 * "宿主 SubPart ID 按整数 → 发射器声明序序号按整数 → 运输者 → 提供者"升序，不能依赖哈希顺序。
 * <p>
 * 身份由结构给出：同一根管的同一条路在两端得到同一个键，因此同一个键可以连续承载很多轮——
 * 轮次不在这个类型里，见 {@link ReloadJob#round()}。四个字段必须一起比较：同一个提供者会被不同的
 * 运输者使用，同一条输弹线也会换架。
 */
class ReloadJobKeyTest {

    private static final AmmoRef TRANSPORTER = new AmmoRef(9, 4);
    private static final AmmoRef PROVIDER = new AmmoRef(9, 5);

    @Test
    void noneIsRecognisedByTheAddressSoAnUnpublishedJobNeverCollides() {
        assertTrue(ReloadJobKey.NONE.isNone());
        assertFalse(new ReloadJobKey(0, 0, TRANSPORTER, PROVIDER).isNone());
    }

    @Test
    void ordersByAddressThenTransporterThenProvider() {
        AmmoRef near = new AmmoRef(1, 1);
        AmmoRef far = new AmmoRef(2, 1);
        List<ReloadJobKey> keys = new ArrayList<>(List.of(
                new ReloadJobKey(7, 2, near, far),
                new ReloadJobKey(3, 9, far, far),
                new ReloadJobKey(7, 1, far, far),
                new ReloadJobKey(7, 1, near, far),
                new ReloadJobKey(7, 1, near, near),
                new ReloadJobKey(3, 1, far, far)
        ));
        keys.sort(ReloadJobKey::compareTo);

        assertEquals(List.of(
                new ReloadJobKey(3, 1, far, far),
                new ReloadJobKey(3, 9, far, far),
                new ReloadJobKey(7, 1, near, near),
                new ReloadJobKey(7, 1, near, far),
                new ReloadJobKey(7, 1, far, far),
                new ReloadJobKey(7, 2, near, far)
        ), keys);
    }

    @Test
    void equalityCoversTheWholeRoute() {
        ReloadJobKey job = new ReloadJobKey(3, 2, TRANSPORTER, PROVIDER);
        assertEquals(job, new ReloadJobKey(3, 2, TRANSPORTER, PROVIDER));
        assertFalse(job.equals(new ReloadJobKey(4, 2, TRANSPORTER, PROVIDER)), "宿主不同即不同管");
        assertFalse(job.equals(new ReloadJobKey(3, 3, TRANSPORTER, PROVIDER)), "发射器不同即不同管");
        assertFalse(job.equals(new ReloadJobKey(3, 2, PROVIDER, PROVIDER)), "运输者不同即不同路");
        assertFalse(job.equals(new ReloadJobKey(3, 2, TRANSPORTER, TRANSPORTER)), "提供者不同即不同路");
    }
}
