Feature: Data Driven Order Testing

  Background:
    Given I map session alias "Upstream" to "FIX.4.2:CLIENT->BROKER"
    And I map session alias "Downstream" to "FIX.4.2:EXCHANGE->BROKER"
    And the session "Upstream" is logged on
    And the session "Downstream" is logged on

  Scenario Outline: Route different instruments and prices successfully
    # Send an order using variables from the Examples table
    When I send a NewOrderSingle with alias "MyOrder" to session "Upstream" with fields:
      | Symbol   | <symbol> |
      | Price    | <price>  |
      | OrderQty | <qty>    |
      | Side     | <side>   |
      | OrdType  | 2        |

    # Verify it arrives downstream with the exact same variables
    Then I expect a routed message with MsgType "D" on session "Downstream" and assign alias "DownstreamOrder" within 5 seconds with fields:
      | Symbol   | <symbol> |
      | Price    | <price>  |
      | OrderQty | <qty>    |

    # The test will loop and run 3 separate times, once for each row below
    Examples:
      | symbol | price   | qty | side |
      | AAPL   | 150.25  | 100 | 1    |
      | MSFT   | 300.50  | 50  | 2    |
      | GOOG   | 2800.00 | 10  | 1    |