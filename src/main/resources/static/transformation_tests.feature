Feature: Message Routing and Tag Transformation Validation

  Background:
    # Ensure both sides of the connection are active before testing
    # Replace these session strings with your actual configured QuickFIX/J Session IDs
    Given the session "FIX.4.2:CLIENT->BROKER" is logged on
    And the session "FIX.4.2:EXCHANGE->BROKER" is logged on

  Scenario: Verify NewOrderSingle is routed to exchange and tag 5000 is mapped to 115
    # The 'alias' automatically generates a unique tag 11 (ClOrdID) and stores it in context
    When I send a NewOrderSingle with alias "Order1" to session "FIX.4.2:CLIENT->BROKER" with fields:
      | Side     | 1      |
      | Symbol   | AAPL   |
      | OrdType  | 2      |
      | Price    | 150.25 |
      | OrderQty | 100    |
      | 5000     | STRATEGY_A |
      
    # Verify it arrives on the exchange session with core fields preserved and custom tags transformed
    Then I expect a message with MsgType "D" on session "FIX.4.2:EXCHANGE->BROKER" for alias "Order1" within 5 seconds with fields:
      | Symbol   | AAPL   |
      | Price    | 150.25 |
      | 115      | STRATEGY_A |