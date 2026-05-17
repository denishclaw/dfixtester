Feature: Order Placement and Tracking

  Scenario: Place order and receive confirmation
    Given the session "FIX.4.4:TEST_SENDER->TEST_EXCHANGE" is logged on
    When I send a NewOrderSingle with alias "Order1" and fields:
      | Symbol          | AAPL   |
      | Side            | 1      |
      | OrdType         | 2      |
      | CustomBrokerTag | BRK123 |
    Then I expect an ExecutionReport for alias "Order1" within 5 seconds
