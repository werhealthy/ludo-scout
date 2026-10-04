package it.vintedaffari.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiBetaComparisonTest {
 @Test public void aggregatedAccessoryProposalNeverErasesLocalSubtype() {
  for (String local : new String[]{"ACCESSORY","COMPONENTS","EMPTY_BOX"}) {
   assertEquals("COMPATIBLE_GROUP",AiBetaComparison.verdict(local,"ACCESSORY_COMPONENT"));
  }
  assertEquals("CONFLICT",AiBetaComparison.verdict("ACCESSORY","BASE_GAME"));
 }
 @Test public void abstentionIsNotAgreementAndConfidenceCannotResolveConflict() {
  assertEquals("UNCERTAIN",AiBetaComparison.verdict("UNCERTAIN","BASE_GAME"));
  assertEquals("UNCERTAIN",AiBetaComparison.verdict("BASE_GAME","UNKNOWN"));
  assertEquals("AGREEMENT",AiBetaComparison.verdict("BUNDLE","BUNDLE"));
  assertEquals("CONFLICT",AiBetaComparison.verdict("EXPANSION","BASE_GAME"));
 }
}
