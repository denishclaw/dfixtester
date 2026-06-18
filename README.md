# DFixTester - System Overview

## High-Level Idea
DFixTester is a comprehensive, web-based FIX (Financial Information eXchange) protocol testing suite. It bridges the gap between manual FIX order entry and automated Behavior-Driven Development (BDD) testing. 

By integrating QuickFIX/J with Cucumber and a dynamic Web GUI, it allows users to easily send FIX messages, parse ATDL (Algorithmic Trading Definition Language) files, and seamlessly execute, visualize, and debug complex multi-session routing scenarios.

---

## System Setup
To run DFixTester effectively, ensure the following directories and files exist at the same level as your executable JAR file (or within your project root if running via an IDE):

* **`config/`**: Contains global configuration files such as:
  * `fix-tag-dictionary.json` (and versioned variants like `fix-tag-dictionary-FIX.4.2.json`)
  * `message-templates.json`
  * `atdl-message-templates.json`
* **`features/`**: The directory where your Cucumber `.feature` BDD test scripts are stored.
* **`atdl/`**: The directory to place your ATDL `.xml` strategy definition files.
* **`multi-order-templates/`**: The directory where JSON templates for sending multiple sequential messages are stored (e.g., `multi-order-templates.json`).

---

## Design Details & Architecture
The system is built on a modern Java Spring Boot stack and consists of three primary layers:

### 1. FIX Engine Backend (QuickFIX/J)
* **`FixApplication`**: Implements the QuickFIX/J `Application` interface to intercept and log all inbound (`fromApp`, `fromAdmin`) and outbound (`toApp`, `toAdmin`) messages.
* **`ScenarioContext`**: The core in-memory state manager. It tracks human-readable order aliases, stores parameter templates, and holds a thread-safe queue of `MessageEvent`s that the testing engine polls.
* **REST Controllers**: Expose clean APIs for session management (`SessionController`), test execution (`TestController`), ATDL parsing (`AtdlController`), and dictionary/template loading.

### 2. Automated Testing Engine (Cucumber)
* **`FixStepDefinitions`**: Contains dynamic `@When` and `@Then` hooks. It uses `Awaitility` to asynchronously poll the `ScenarioContext` message queue, allowing for robust validation of routed messages, field transformations, and Admin rejects.
* **`LiveTestReporter`**: A custom Cucumber plugin that intercepts live execution events and streams them to the frontend as JSON (`@@TEST_EVENT@@` and `@@TEST_MSG@@`).

### 3. Frontend Web GUI (Vanilla JS + Bootstrap 5)
* **Session Management**: Real-time logon, logout, and sequence reset controls.
* **Order Entry & ATDL**: Dynamic FIX tag builders and an on-the-fly ATDL XML parser that generates algorithmic forms. Built messages can be exported instantly into Cucumber step formats.
* **Test Runner Dashboard**: Live-streams Cucumber execution, tracking steps line-by-line. Features a rich side-by-side FIX message comparison view that explicitly highlights validated, missing, and mismatched tags in green and yellow.
* **System Logs**: Streams raw QuickFIX/J socket logs via `SystemLogCapture` for deep debugging.

---

## Cucumber Steps Reference
Here is the complete list of available step definitions for your `.feature` scenarios:

### Configuration & Session Management
* `Given I map session alias "{alias}" to "{sessionString}"`
* `Given the session "{sessionString}" is logged on`
* `Given I define parameter template "{templateName}" with fields:` (Followed by Data Table)

### Sending Messages
* `When I send a NewOrderSingle with alias "{alias}" and fields:` (Followed by Data Table)
* `When I send a NewOrderSingle with alias "{alias}" to session "{sessionString}" with fields:` (Followed by Data Table)
* `When I send a NewOrderSingle with alias "{alias}" to session "{sessionString}" using templates "{templateList}"`
* `When I send a NewOrderSingle with alias "{alias}" to session "{sessionString}" using templates "{templateList}" with fields:` (Followed by Data Table)

### Validating Messages
* `Then I expect an ExecutionReport for alias "{alias}" within {timeout} seconds`
* `Then I expect a message with MsgType "{msgType}" on session "{sessionString}" for alias "{alias}" within {timeout} seconds with fields:` (Followed by Data Table)
* `Then I expect a routed message with MsgType "{msgType}" on session "{sessionString}" and assign alias "{newAlias}" within {timeout} seconds with fields:` (Followed by Data Table)
* `Then I expect an admin message with MsgType "{msgType}" on session "{sessionString}" within {timeout} seconds with fields:` (Followed by Data Table)