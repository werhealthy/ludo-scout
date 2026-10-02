package it.vintedaffari.app;

import android.graphics.Rect;

public final class VintedCard {
    public final String title;
    public final String brand;
    public final String condition;
    public final double itemPrice;
    public final Double protectedPrice;
    public final Integer favorites;
    public final Rect bounds;
    public final String rawDescription;
    public final String sellerName;
    public final String itemId,itemUrl,captureSource;

    public VintedCard(String title,String brand,String condition,double itemPrice,Double protectedPrice,Integer favorites,Rect bounds,String rawDescription) {
        this(title,brand,condition,itemPrice,protectedPrice,favorites,bounds,rawDescription,"");
    }

    public VintedCard(String title,String brand,String condition,double itemPrice,Double protectedPrice,Integer favorites,Rect bounds,String rawDescription,String sellerName) {
        this(title,brand,condition,itemPrice,protectedPrice,favorites,bounds,rawDescription,sellerName,"","","");
    }
    public VintedCard(String title,String brand,String condition,double itemPrice,Double protectedPrice,Integer favorites,Rect bounds,String rawDescription,String sellerName,String itemId,String itemUrl,String captureSource) {
        this.itemId=itemId==null?"":itemId;this.itemUrl=itemUrl==null?"":itemUrl;this.captureSource=captureSource==null?"":captureSource;
        this.title = title;
        this.brand = brand;
        this.condition = condition;
        this.itemPrice = itemPrice;
        this.protectedPrice = protectedPrice;
        this.favorites = favorites;
        this.bounds = new Rect(bounds);
        this.rawDescription = rawDescription;
        this.sellerName = sellerName == null ? "" : sellerName.trim();
    }
}
