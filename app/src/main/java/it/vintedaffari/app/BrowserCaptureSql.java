package it.vintedaffari.app;
import android.database.sqlite.SQLiteDatabase;
/** Additive browser staging; shared literal queries are exercised with real SQLite fixtures. */
public final class BrowserCaptureSql {
 private BrowserCaptureSql(){}
 public static final String CREATE_CAPTURES = "CREATE TABLE IF NOT EXISTS browser_captures(id INTEGER PRIMARY KEY AUTOINCREMENT,url TEXT NOT NULL,started_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'CAPTURING')";
 public static final String CREATE_CANDIDATES = "CREATE TABLE IF NOT EXISTS browser_candidates(item_id TEXT PRIMARY KEY,url TEXT NOT NULL,title TEXT NOT NULL,price_cents INTEGER,protected_price_cents INTEGER,payload TEXT NOT NULL DEFAULT '{}',content_key TEXT NOT NULL DEFAULT '',revision INTEGER NOT NULL DEFAULT 1,processed_revision INTEGER NOT NULL DEFAULT 0,observed_at INTEGER NOT NULL,state TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',lease_started_at INTEGER NOT NULL DEFAULT 0,claimed_revision INTEGER NOT NULL DEFAULT 0,listing_id INTEGER,completed_at INTEGER NOT NULL DEFAULT 0,CHECK(price_cents IS NULL OR price_cents>0),CHECK(revision>0))";
 public static final String CREATE_MEMBERSHIP = "CREATE TABLE IF NOT EXISTS browser_capture_items(capture_id INTEGER NOT NULL,item_id TEXT NOT NULL,page INTEGER NOT NULL,first_observed_at INTEGER NOT NULL,last_observed_at INTEGER NOT NULL,revision INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(capture_id,item_id))";
 public static final String CREATE_MEMBERSHIP_INDEX = "CREATE INDEX IF NOT EXISTS idx_browser_membership_item ON browser_capture_items(item_id,capture_id)";
 public static final String CREATE_STATE_INDEX = "CREATE INDEX IF NOT EXISTS idx_browser_state_observed ON browser_candidates(state,observed_at,item_id)";
 public static final String UPSERT_MEMBERSHIP = "INSERT OR IGNORE INTO browser_capture_items(capture_id,item_id,page,first_observed_at,last_observed_at,revision,payload) VALUES(?,?,?,?,?,?,?)";
 public static final String UPDATE_MEMBERSHIP = "UPDATE browser_capture_items SET page=?,last_observed_at=?,revision=?,payload=? WHERE capture_id=? AND item_id=?";
 public static final String CAPTURE_HEADERS = "SELECT c.id,c.url,c.started_at,c.updated_at,c.state,COUNT(b.item_id),SUM(CASE WHEN b.price_cents IS NOT NULL THEN 1 ELSE 0 END) FROM browser_captures c LEFT JOIN browser_capture_items m ON m.capture_id=c.id LEFT JOIN browser_candidates b ON b.item_id=m.item_id GROUP BY c.id ORDER BY c.started_at DESC,c.id DESC LIMIT ?";
 public static final String OTHER_JOB_WHERE = "j.state IN ('PENDING','PROCESSING','FAILED_RETRYABLE') AND NOT EXISTS(SELECT 1 FROM browser_capture_items m JOIN browser_candidates b ON b.item_id=m.item_id LEFT JOIN market_listings l ON l.id=b.listing_id WHERE m.capture_id=? AND (j.job_key='browser_analysis:'||b.item_id OR j.listing_id=b.listing_id OR (j.game_id IS NOT NULL AND j.game_id=l.game_id)))";
 public static final String COMPLETE_CURRENT = "UPDATE browser_candidates SET state=?,reason=?,processed_revision=?,completed_at=?,lease_started_at=0,claimed_revision=0 WHERE item_id=? AND revision=? AND lease_started_at=?";
 public static void create(SQLiteDatabase db){db.execSQL(CREATE_CAPTURES);db.execSQL(CREATE_CANDIDATES);db.execSQL(CREATE_MEMBERSHIP);db.execSQL(CREATE_MEMBERSHIP_INDEX);db.execSQL(CREATE_STATE_INDEX);}
}
