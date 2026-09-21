package it.vintedaffari.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductPageParser {
    private static final Pattern PRICE=Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*€");
    private ProductPageParser(){}

    public static ProductPage parse(AccessibilityNodeInfo root){
        if(root==null)return null;
        ProductPage p=new ProductPage();
        walk(root,p);
        return p.isValid()?p:null;
    }

    private static void walk(AccessibilityNodeInfo n, ProductPage p){
        if(n==null)return;
        String id=n.getViewIdResourceName();
        String text=n.getText()==null?"":n.getText().toString().trim();
        String desc=n.getContentDescription()==null?"":n.getContentDescription().toString().trim();
        Rect b=new Rect(); n.getBoundsInScreen(b);
        if(id!=null){
            String lid=id.toLowerCase(Locale.ROOT);
            if(id.endsWith("item_screen_content")) p.contentBounds.set(b);
            else if(id.endsWith("item_image_gallery")){p.imageBounds.set(b);}
            else if(id.endsWith("item_summary_content")) p.summaryBounds.set(b);
            else if(id.endsWith("item_summary_line_1") && !text.isEmpty()) p.title=text;
            else if(id.endsWith("item_summary_line_2") && !text.isEmpty()) parseSummary(text,p);
            else if(id.endsWith("total_price") && !desc.isEmpty()) p.protectedPrice=firstPrice(desc);
            // Capture only semantically product-specific text. Do not concatenate the whole screen:
            // recommendation/seller carousels can contain unrelated products and would poison language/type.
            if((lid.contains("description")||lid.contains("details")||lid.contains("category")||lid.contains("catalog")||lid.contains("language")) && (!text.isEmpty()||!desc.isEmpty())){
                String hint=(text+" "+desc).trim();
                if(!hint.isEmpty()&&!p.detailsText.contains(hint)){
                    if(!p.detailsText.isEmpty())p.detailsText += " · ";
                    if(p.detailsText.length()<4000)p.detailsText += hint.substring(0,Math.min(hint.length(),Math.max(0,4000-p.detailsText.length())));
                }
            }
            if(p.sellerName.isEmpty() && (lid.contains("user")||lid.contains("seller")||lid.contains("member")) &&
                    (lid.contains("name")||lid.contains("login")||lid.contains("profile"))){
                String candidate=!text.isEmpty()?text:desc;candidate=candidate.replaceFirst("^@","").trim();
                if(candidate.matches("[A-Za-z0-9._-]{2,40}"))p.sellerName=candidate;
            }
        }
        if(p.itemPrice<=0 && !text.isEmpty() && text.contains("€") && (id==null || !id.endsWith("footer_protection_note"))){
            Double v=firstPrice(text); if(v!=null)p.itemPrice=v;
        }
        String combined=(text+" "+desc).trim();
        String lower=combined.toLowerCase(Locale.ROOT);
        if(lower.matches(".*\\b(venduto|venduta|sold)\\b.*")
                ||lower.contains("non più disponibile")
                ||lower.contains("non e più disponibile")
                ||lower.contains("non è disponibile")
                ||lower.contains("articolo non disponibile")
                ||lower.contains("item unavailable"))p.sold=true;
        if(!combined.isEmpty() && combined.toLowerCase(Locale.ROOT).contains("spedizione") && combined.contains("€")){
            Double v=firstPrice(combined); if(v!=null)p.shippingPrice=v;
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)walk(c,p);}
    }

    private static void parseSummary(String s, ProductPage p){
        String[] parts=s.split("·");
        if(parts.length>0)p.condition=parts[0].trim();
        if(parts.length>1)p.brand=parts[1].trim();
        if(parts.length>2){StringBuilder x=new StringBuilder();for(int i=2;i<parts.length;i++){if(x.length()>0)x.append(" · ");x.append(parts[i].trim());}p.publishedLabel=x.toString();}
    }
    private static Double firstPrice(String s){Matcher m=PRICE.matcher(s);if(!m.find())return null;try{return Double.parseDouble(m.group(1).replace(',','.'));}catch(Exception e){return null;}}
}
