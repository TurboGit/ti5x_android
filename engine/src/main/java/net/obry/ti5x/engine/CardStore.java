package net.obry.ti5x.engine;

import java.util.Map;
import java.util.HashMap;

public class CardStore {
  final Map<Integer, Card> Store = new HashMap<Integer, Card>();

  public Boolean Contains
      (
          int id
      ) {
    return Store.containsKey(id);
  }

  public Card Get
      (
          int id
      ) {
    return Store.get(id);
  }

  public void SetMem
      (
          int Id,
          int BankNr,
          Number[] Data
      ) {
    Card C;
    if (Store.containsKey(Id)) {
      C = Store.get(Id);
    } else {
      C = new Card(BankNr);
      Store.put(Id, C);
    }

    C.SetMem (Data);
  }

  public void SetProg
      (
          int Id,
          int BankNr,
          byte[] Data
      ) {
    Card C;
    if (Store.containsKey(Id)) {
      C = Store.get(Id);
    } else {
      C = new Card(BankNr);
      Store.put(Id, C);
    }

    C.SetProg (Data);
  }
}
