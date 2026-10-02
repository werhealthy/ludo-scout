package it.vintedaffari.app;
import android.test.AndroidTestCase;
import androidx.work.NetworkType;
public class BrowserSchedulingTest extends AndroidTestCase {
 public void testLocalWakeAndRecoveryHaveNoNetworkConstraint(){assertEquals(NetworkType.NOT_REQUIRED,QueueWorkScheduler.browserRequest().getWorkSpec().constraints.getRequiredNetworkType());assertEquals(NetworkType.NOT_REQUIRED,QueueWorkScheduler.browserRecoveryRequest().getWorkSpec().constraints.getRequiredNetworkType());assertEquals(15*60_000L,QueueWorkScheduler.browserRecoveryRequest().getWorkSpec().intervalDuration);}
}
