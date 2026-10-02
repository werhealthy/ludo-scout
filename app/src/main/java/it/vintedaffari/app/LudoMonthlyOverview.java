package it.vintedaffari.app;
import java.util.Calendar;
import java.util.TimeZone;
/** Read-only UI summary of locally observed listings, never the imported BGG corpus. */
final class LudoMonthlyOverview {
 static final String STATIC_QUERY="SELECT COUNT(*),COALESCE(SUM(CASE WHEN rating>6.0 THEN 1 ELSE 0 END),0) FROM (SELECT g.bgg_id,MAX(g.rating) AS rating FROM market_listings l JOIN games g ON g.id=l.game_id WHERE l.first_seen>=? AND l.first_seen<=? AND g.database_visible=1 AND g.match_state='MATCHED' AND TRIM(COALESCE(g.bgg_id,''))<>'' AND l.lifecycle IN ('ACTIVE','ARCHIVED','REMOVED') GROUP BY g.bgg_id)";
 static long monthStart(long now){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Europe/Rome"));c.setTimeInMillis(now);c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
}
