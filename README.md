
# one-stop-shop-returns

This is the repository for One Stop Shop Returns Backend

Frontend: https://github.com/hmrc/one-stop-shop-returns-frontend

Stub: https://github.com/hmrc/one-stop-shop-returns-stub

For more details on the One Stop Shop Returns service, including how to use the application, please refer
to the instructions in the one-stop-shop-returns-frontend repository.

Requirements
------------

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## Run the application locally via Service Manager

```
sm2 --start ONE_STOP_SHOP_ALL -r
```

### To run the application locally from the repository, execute the following:
```
sm2 --stop ONE_STOP_SHOP_RETURNS
```
and 
```
sbt run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes
```

### Running correct version of mongo
Mongo 6 with a replica set is required to run the service. Please refer to the MDTP Handbook for instructions on how to run this


Unit and Integration Tests
------------

To run the unit and integration tests, you will need to open an sbt session on the browser.

### Unit Tests

To run all tests, run the following command in your sbt session:
```
test
```

To run a single test, run the following command in your sbt session:
```
testOnly <package>.<SpecName>
```

An asterisk can be used as a wildcard character without having to enter the package, as per the example below:
```
testOnly *CorrectionControllerSpec
```

### Integration Tests

To run all tests, run the following command in your sbt session:
```
it:test
```

To run a single test, run the following command in your sbt session:
```
it:testOnly <package>.<SpecName>
```

An asterisk can be used as a wildcard character without having to enter the package, as per the example below:
```
it:testOnly *CorrectionRepositorySpec
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
