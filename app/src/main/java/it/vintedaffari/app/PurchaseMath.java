package it.vintedaffari.app;
/** Money is kept in cents. Vinted's usual fee is an editable estimate, not a tariff guarantee. */
public final class PurchaseMath {
 private PurchaseMath(){}
 public static int vintedFee(int itemCents){if(itemCents<0)throw new IllegalArgumentException("Prezzo negativo");return Math.addExact(70,(int)Math.round(itemCents*0.05));}
 public static Integer total(Integer item,Integer shipping,Integer fee){if(item==null||shipping==null||fee==null)return null;return Math.addExact(Math.addExact(item,shipping),fee);}
 public static int estimatedTotal(int itemCents,int shippingCents){if(itemCents<0||shippingCents<0)throw new IllegalArgumentException("Importo negativo");return Math.addExact(Math.addExact(itemCents,shippingCents),vintedFee(itemCents));}
}
