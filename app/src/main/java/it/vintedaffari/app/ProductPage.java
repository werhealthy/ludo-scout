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

    public boolean hasProductStructure() {
        return !contentBounds.isEmpty() || !imageBounds.isEmpty() || !summaryBounds.isEmpty() || !priceBounds.isEmpty();
    }

    public boolean isValid() {
        boolean hasTitle=title != null && !title.trim().isEmpty();
        // Sold pages can suppress price or move the title to a different accessibility node.
        // Structural product-page evidence + an explicit sold/unavailable signal is enough when
        // Ludo already has exact outbound provenance for the listing.
        return (hasTitle && itemPrice > 0) || (sold && (hasTitle || hasProductStructure()));
    }

    public VintedCard asCard() {
        Rect b = !contentBounds.isEmpty() ? contentBounds : (!imageBounds.isEmpty() ? imageBounds : new Rect(0, 0, 1080, 1800));
        String raw=title + ", brand: " + brand + ", condizioni: " + condition;
        if(detailsText!=null&&!detailsText.trim().isEmpty())raw += ", dettagli: " + detailsText.trim();
        return new VintedCard(title, brand, condition, itemPrice, protectedPrice, favorites,
                b, raw, sellerName);
    }
}
