# Backend.AI Client Library for Java

## Install

Clone this repository and use gradle to build the target jar file.

```console
$ ./gradlew jar
```

You will get the jar file at `backend.ai-client/build/libs` directory. To create an archived library, use `distZip` task.

```console
$ ./gradlew distZip
```

Check `backend.ai-client/build/distributions` for result.

## Usage

Check out a simple tester code at `backend.ai-client-tester` sub-project,
which demonstrate configuration loading and a query-mode execution loop.

You can test client-tester with gradle by using `PappArgs` options

```console
BACKEND_ACCESS_KEY="XXXXXXXXXXXXXXXXXXXX" \
BACKEND_SECRET_KEY="XXXXXXXXXXXXXXXXXXXX" \
./gradlew :backend.ai-client-tester:run \
-PappArgs="['-k','<<KERNEL TYPE>>','-f','<<SOURCE FILE PATH>>'"
```
