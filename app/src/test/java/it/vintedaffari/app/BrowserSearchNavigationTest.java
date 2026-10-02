package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class BrowserSearchNavigationTest {
 @Test public void multipleFiltersPreserved(){String url=BrowserSearchNavigation.build("https://www.vinted.it/catalog/4881-board-games?brand_ids%5B%5D=12&status_ids%5B%5D=2&status_ids%5B%5D=3&page=3","Azul","newest_first",1200,3000,3);assertTrue(url.contains("brand_ids%5B%5D=12"));assertTrue(url.contains("status_ids%5B%5D=2"));assertTrue(url.contains("status_ids%5B%5D=3"));assertTrue(url.contains("price_from=12"));assertTrue(url.contains("price_to=30"));assertTrue(url.contains("page=1"));}
 @Test public void removingMinimumKeepsMaximum(){String url=BrowserSearchNavigation.build("https://www.vinted.it/catalog/4881-board-games?price_from=12&price_to=30&page=3","","relevance",null,3000,3);assertFalse(url.contains("price_from"));assertTrue(url.contains("price_to=30"));assertTrue(url.contains("page=1"));}
 @Test public void pageChangeKeepsFilters(){String url=BrowserSearchNavigation.build("https://www.vinted.it/catalog/4881-board-games?search_text=Azul&order=newest_first&price_from=12&price_to=30&page=3","Azul","newest_first",1200,3000,4);assertTrue(url.contains("page=4"));assertTrue(url.contains("search_text=Azul"));}
 @Test public void decimalPriceAndUnicodeAreEncoded(){String url=BrowserSearchNavigation.build("https://www.vinted.it/catalog","échelle & Catan","relevance",1250,null,1);assertTrue(url.contains("price_from=12.5"));assertTrue(url.contains("search_text=%C3%A9chelle+%26+Catan"));}
 @Test(expected=IllegalArgumentException.class) public void refusesEleventhPage(){BrowserSearchNavigation.build("https://www.vinted.it/catalog","","relevance",null,null,11);}
 @Test(expected=IllegalArgumentException.class) public void refusesWrongOrigin(){BrowserSearchNavigation.build("https://example.com/catalog","","relevance",null,null,1);}
 @Test(expected=IllegalArgumentException.class) public void refusesInvertedRange(){BrowserSearchNavigation.build("https://www.vinted.it/catalog","","relevance",3000,1000,1);}
}
