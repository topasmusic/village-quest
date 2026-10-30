package de.quest.commands;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
final class PersonalResetConfirmationsTest {
 @Test void ticketsRequireSameOwnerServerAndAreConsumedOnce() {
  var c=new PersonalResetConfirmations();var server=new Object();var owner=UUID.randomUUID();
  assertFalse(c.consume(server,owner,100));c.request(server,owner,100);
  assertFalse(c.consume(server,UUID.randomUUID(),101));
  assertTrue(c.consume(server,owner,102));assertFalse(c.consume(server,owner,103));
 }
 @Test void expiryIsExclusiveAndWrongServerCannotConsumeForAReset() {
  var c=new PersonalResetConfirmations();var server=new Object();var owner=UUID.randomUUID();
  c.request(server,owner,0);assertFalse(c.consume(server,owner,120000));
  c.request(server,owner,0);assertFalse(c.consume(new Object(),owner,1));
  assertFalse(c.consume(server,owner,2));
  c.request(server,owner,0);assertTrue(c.consume(server,owner,119999));
 }
 @Test void anotherRequestReplacesThePendingWindowWithoutExecuting() {
  var c=new PersonalResetConfirmations();var server=new Object();var owner=UUID.randomUUID();
  c.request(server,owner,0);c.request(server,owner,1000);
  assertTrue(c.consume(server,owner,120000));assertFalse(c.consume(server,owner,120001));
 }
}