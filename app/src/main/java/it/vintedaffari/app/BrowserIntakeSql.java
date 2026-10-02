package it.vintedaffari.app;

/** Existing SQLite tables only. Identity comes from the exact Vinted item ID. */
final class BrowserIntakeSql {
    static final String LOOKUP = "SELECT id,COALESCE(NULLIF(legacy_signature,''),temp_fingerprint),vinted_title,brand,item_condition,current_price_cents,protected_price_cents,lifecycle,manual_review_required,enrichment_state,observed_text FROM market_listings WHERE vinted_item_id=? LIMIT 1";
    static final String INSERT = "INSERT OR IGNORE INTO market_listings(temp_fingerprint,legacy_signature,vinted_item_id,vinted_title,brand,item_condition,current_price_cents,protected_price_cents,observed_text,vinted_url,first_seen,last_seen,enrichment_state,match_state,lifecycle) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')";
    static final String UPDATE = "UPDATE market_listings SET vinted_title=?,brand=COALESCE(NULLIF(?,''),brand),item_condition=COALESCE(NULLIF(?,''),item_condition),protected_price_cents=CASE WHEN current_price_cents<>? THEN ? ELSE COALESCE(?,protected_price_cents) END,current_price_cents=?,observed_text=?,vinted_url=?,image_url=COALESCE(NULLIF(?,''),image_url),listing_photos_csv=COALESCE(NULLIF(?,''),listing_photos_csv),seller_id=COALESCE(NULLIF(?,''),seller_id),seller_name=COALESCE(NULLIF(?,''),seller_name),published_label=COALESCE(NULLIF(?,''),published_label),language_code=COALESCE(NULLIF(?,''),language_code),last_seen=? WHERE id=? AND lifecycle='ACTIVE' AND COALESCE(manual_review_required,0)=0";
    static final String PROVENANCE = "INSERT OR REPLACE INTO queue_controls(name,value,updated_at,text_value) VALUES(?,?,?,?)";
    static final String BROWSER_OWNED = "SELECT 1 FROM queue_controls WHERE name='browser_listing:'||? AND value=1";
    private BrowserIntakeSql() {}
}
