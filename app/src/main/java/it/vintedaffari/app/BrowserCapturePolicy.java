package it.vintedaffari.app;

/** Validation of already received public data; never fetches or infers missing fields. */
final class BrowserCapturePolicy {
    static final String READY_ACTION="it.vintedaffari.app.BROWSER_INTAKE_READY";
    static Integer priceCents(Object value,String currency) {
        if(!(value instanceof Number)||!"EUR".equals(currency))return null;
        double n=((Number)value).doubleValue();
        return Double.isFinite(n)&&n>0&&n<=1_000_000_000&&n==Math.rint(n)?(int)n:null;
    }
    static String photo(String url) {
        if(url==null||url.length()>2048)return "";
        try{java.net.URI u=new java.net.URI(url);String h=u.getHost();
            boolean trusted=h!=null&&(h.equals("vinted.net")||h.endsWith(".vinted.net")||h.equals("vinted.com")||h.endsWith(".vinted.com"));
            return "https".equals(u.getScheme())&&u.getUserInfo()==null&&trusted?url:"";
        }catch(Exception ignored){return "";}
    }
    static boolean explicitRequest(String source) {
        return "MANUAL_PRIORITY".equals(source)||"MANUAL_RECOVERY".equals(source)||"OPENED_VERIFY".equals(source)||"HUNT_PRIORITY".equals(source);
    }
    private BrowserCapturePolicy() {}
}
