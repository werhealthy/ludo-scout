package it.vintedaffari.app;

import android.graphics.Rect;

public final class ProductPage {
    public String title = "";
    public String condition = "";
    public String brand = "";
    public String publishedLabel = "";
    public String sellerName = "";
    /** Structured text exposed by the Vinted product page (description/category/language hints). */
    public String detailsText = "";
    /** Category evidence exposed by the product page; never inferred when Vinted omits it. */
    public String categoryRaw = "";
    public String categoryNormalized = "";
    public String categorySource = "";
    public int categoryConfidence = 0;
    public double itemPrice = 0;
    public Double protectedPrice = null;
    public Integer favorites = null;
    public boolean sold = false;
    public Double shippingPrice = null;
    public int imageCount = 0;
    public Rect contentBounds = new Rect();
    public Rect imageBounds = new Rect();
    public Rect summaryBounds = new Rect();
    public Rect priceBounds = new Rect();

    public void captureCategory(String raw,String source){
        if(raw==null)return;String value=raw.trim();if(value.length()<3||value.equalsIgnoreCase("category")||value.equalsIgnoreCase("catalog"))return;
        if(categoryRaw.isEmpty()){categoryRaw=value;categoryNormalized=normalizeCategory(value);categorySource=source==null?"accessibility_product_page":source;categoryConfidence=80;}
    }
    private static String normalizeCategory(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}

    public boolean hasProductStructure() {
        return !contentBounds.isEmpty() || !imageBounds.isEmpty() || !summaryBounds.isEmpty() || !priceBounds.isEmpty();
    }

    public boolean isValid() {
        boolean hasTitle=title != null && !title.trim().isEmpty();
        return (hasTitle && itemPrice > 0) || (sold && (hasTitle || hasProductStructure()));
    }

    public VintedCard asCard() {
        Rect b = !contentBounds.isEmpty() ? contentBounds : (!imageBounds.isEmpty() ? imageBounds : new Rect(0, 0, 1080, 1800));
        String raw=title + ", brand: " + brand + ", condizioni: " + condition;
        if(detailsText!=null&&!detailsText.trim().isEmpty())raw += ", dettagli: " + detailsText.trim();
        if(categoryRaw!=null&&!categoryRaw.trim().isEmpty())raw += ", categoria Vinted: " + categoryRaw.trim();
        return new VintedCard(title, brand, condition, itemPrice, protectedPrice, favorites,
                b, raw, sellerName);
    }
}
