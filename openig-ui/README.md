<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions copyright [year] [name of copyright owner]".

  Copyright 2016 ForgeRock AS.
  -->

# OpenIG UI

The UI project aims to build a modern web user interface for most used OpenIG scenarios, making complex tasks
easier to perform.

# Built It

## Pre-requisites

* [Apache Maven](http://maven.apache.org/download.cgi) (Pull all NodeJS dependencies from NPM)
* [Grunt CLI](http://gruntjs.com/using-the-cli) (If you intend to execute grunt from the command line)

## Maven

OpenIG UI uses Maven as a build orchestrator to download dependencies, and then delegate to grunt/npm for
dedicated JavaScript build tasks.

```
>$ mvn clean install

Scanning for projects...

------------------------------------------------------------------------
Building OpenIG User Interface 5.0.0-SNAPSHOT
------------------------------------------------------------------------

...

Running "requirejs:compile" (requirejs) task

Done, without errors.
------------------------------------------------------------------------
BUILD SUCCESS
------------------------------------------------------------------------
Total time: 27.323 s
Finished at: 2016-06-24T17:17:31+02:00
Final Memory: 18M/59M
------------------------------------------------------------------------
```

# Try It

This is as simple as:

```
>$ grunt web

Running "sync:source" (sync) task
Updating file target/www/config/process/CommonConfig.js
Updating file target/www/config/routes/CommonRoutesConfig.js
Updating file target/www/main.js

Running "sync:test" (sync) task
Copying src/test/js/config.js -> target/test/config.js
Copying src/test/js/run.js -> target/test/run.js
Creating target/test/tests
Copying src/test/js/tests/OpenIGValidatorsTests.js -> target/test/tests/OpenIGValidatorsTests.js
Copying src/test/js/tests/getLoggedUser.js -> target/test/tests/getLoggedUser.js
Copying src/test/resources/qunit.html -> target/test/qunit.html

Running "less:compile" (less) task
File target/www/css/structure.css created
File target/www/css/theme.css created

Running "serve" task
Server is running on port 9000...
Press CTRL+C at any time to terminate it.
```

As you may expect, just go to [http://localhost:9000](http://localhost:9000/index.html) to try the application.

You'll be prompted for credentials; please use `test:test` and click "Login".
