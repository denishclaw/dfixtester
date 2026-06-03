Feature: Order Placement and Tracking

  Background:
  # 1. Define your variables here
  Given I map session alias "Upstream" to "FIX.4.2:CLIENT->BROKER"
  And I map session alias "Downstream" to "FIX.4.2:EXCHANGE->BROKER"
  
  # 2. Use them everywhere else!
  And the session "Upstream" is logged on
  And the session "Downstream" is logged on
  
  Scenario: Place order and receive confirmation
    Given the session "Upstream" is logged on
    When I send a NewOrderSingle with alias "Order1" and fields:
      | Symbol          | AAPL   |
      | Side            | 1      |
      | OrdType         | 2      |
      | CustomBrokerTag | BRK123 |
    Then I expect an ExecutionReport for alias "Order1" within 5 seconds
