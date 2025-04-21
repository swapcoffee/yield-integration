# swap.coffee Yield Protocol Integration

At swap.coffee, we not only aggregate DEXs but also support yield protocols. We are now expanding our yield aggregator
and seeking partners interested in integrating with us. Upon integration, you will gain:

1. Partnership with a tier-1 protocol on the TON network.
2. Access to thousands of new users who use the swap.coffee platform daily.
3. A significant increase in Total Value Locked (TVL) for your protocol.

## How to integrate your protocol with swap.coffee

1. Fork the project and clone it. If you prefer, you can create a private project. In that case, add the dev
   team to your repository: `@glcanvas` and `@RinesThaix`.
2. Create a new branch in your repo called `integration/<your-protocol-name>`.
3. Implement and test the required classes (see below).
4. Submit a merge request and assign it to the dev team.
5. Notify one of the admins from the Telegram chat: https://t.me/swapcoffee_dev_chat about your merge request.
6. We will review your request. If there are any issues, we will comment on them in the pull request. You will need to resolve them.
7. After that, your protocol will appear on the [Earn page](https://swap.coffee/earn).

## How to run the application locally

1. Download and install a JetBrains IDE (the Community version is enough).
2. Open the project in IntelliJ IDEA and let the IDE download the dependencies.
3. Build the containers from `docker-compose.yml`.
4. Launch the `Application.kt`.
5. The Swagger UI is available at http://localhost:8080/swagger-ui.

## To integrate your protocol with swap.coffee, please follow these steps:

1. Define the objects used in GET and POST requests and internal data classes.
2. Create a service to load pool data, trading statistics, and other information.
3. Develop a service that responds to REST API queries and integrate it with `YieldService`.
4. Implement a tracking process to monitor transactions.

## Project structure

The project consists of several components, including workers, parsers, handlers, background loaders, and a REST API.

- The StonFi integration example is located in the `src/main/kotlin/com/example/protocols/stonfi` folder.
- The empty classes for your new protocol, which you need to implement, are located in the
  `src/main/kotlin/com/example/protocols/newprotocol` directory.

The `StonfiV1Parser` identifies transactions with an opcode of `0x25938561` and parses them, and creates a new pool if
necessary.
It also updates trading metrics.

Next, the data is processed in the background and distributed among backend servers using the `LoaderService`.
The `LoaderService` periodically calls the `doWork` function, which can either load
all data from the database or fetch it from an external API.

Let’s examine the `StonfiV1LoaderService` class. It loads all known pools, calculates trading statistics, and then loads
boosts. This data is subsequently published to the `YieldBoostsService` and `YieldTradingStatisticsService`.

If you prefer not to implement custom parsers (`BlockchainRecordParser`), but prefer to use external APIs to load data,
ensure that the pools are saved in the `liquidity_pools` table, because it will be used later by `YieldService`.

The `YieldService` is a critical component of the system. All API queries are sent to this service.
The `YieldService` retrieves data from various protocol sources, fetches boosts and metrics, and responds to user search
queries, handling all user requests.

### GET and POST Request Objects, and internal data classes

The `YieldService` exposes the following four endpoints:

1. `GET /v1/yield/pools`: Returns a list of pools based on specified criteria. The response should include only basic
   pool details, such as name, protocol, tokens, and trading metrics.
2. `GET /v1/yield/pool/{pool_address}`: Returns detailed and **actual** information about a pool, including:
    - Active boosts
    - Fees
    - Reserves
    - Total supply
3. `GET /v1/yield/pool/{pool_address}/{user_address}`: Returns user-specific information about the pool, including:
    - User’s balance
    - User rewards
    - Deposits
    - User's locked tokens
4. `POST /v1/yield/pool/{pool_address}/{user_address}`: Creates transactions to interact with the pool, supporting
   actions such as deposits, withdrawals, and claiming rewards.

These endpoints located in the `yield-spec.yaml`. Go through the file and add resolve all `TODO`.
Please, note:
`YieldSearchResponseApi`, `YieldUserDetailApi`, and `InteractionRequestApi` objects use a `oneOf` structure, where
resolution is based on the `yieldTypeResolver` field. **Do not use the `yieldTypeResolver` field in your own objects**,
as it is
reserved for the system.

Next, you must define protocol in file `YieldProtocols.kt`.
Specify data class which corresponds to this protocol in `YieldPoolFields.kt`.
Next, consider boosts (farms). If you have boosts structure which significantly differs from `YoeldBoostDex`, define
your own at `YieldBoost.kt`.

### Loading Pools and Calculating Metrics

To load new pools and calculate trading metrics, you have two options:

1. **Use a Parser with `LoaderService`**: parse latest transactions from blockchain and process it in the
   `LoaderService`. To
   do this:
    - Implement the `BlockchainRecordParser` interface using the `NewProtocolRecordParser` template. This parser should
      be triggered when a transaction belongs to your protocol and should return trading metrics and new pools as
      `BlockchainRecords`.
    - Implement the `LoaderService` using the `LoaderServiceNewProtocol` template. The service will periodically fetch
      new data, calculate metrics, and update boosts. For more details, refer to `StonfiV1LoaderService`.
2. **Direct Implementation in `LoaderService`**: Fetch data directly within the `LoaderService` using `AsyncHttpClient`
   from external sources, such as your API or
   the <a href="https://indexer.swap.coffee/swagger-ui">https://indexer.swap.coffee/swagger-ui</a> endpoint.

Once new pools are fetched, they must be stored in the database. Refer to `StonfiV1LoaderService` for more information.

### Creating a REST API Service

Create a custom service, such as `YourProtocolNameService`, to respond to REST API requests. This service should:

- **Return Detailed Pool Information**: Since `LoaderService` updates periodically, it cannot provide real-time data
  like `total_supply` and `reserve_ratio`. Your service should fetch and return the latest data for users.
- **Return list of pools where user has a liquidity**: Because we can't store all user's data, and support up-to-date
  information about user's positions
  on our side, we'll ask you to upload it through the Indexer or through your REST API.
- **Provide User Information**: swap.coffee does not store latest user information in its database. Use the Indexer API
  or query a TON node to retrieve user-specific data about the pool.
- **Create Transactions for Pool Interaction**: This service must create transactions which will help users to interact
  with your service.

While `YourProtocolNameService` creation, you may define any functions and pass any parameters to your service.
However, ensure you add your new service to `YieldService` and extend the following functions:
`getUserDetails`, `getAllPoolsForUser`, `processUserRequest`, `mapper`, and `mapperDetail`.

### Implementing Transaction Tracing

To track transaction status, implement a tracing process using the `StatusHandler` class. This class is passed to the
parser to resolve transaction status. Currently, two types of resolvers are available:

- `StatusHandlerOnReceive`: Marks a transaction as successful when it is executed on the blockchain.
- `StatusHandlerEmptyOutputEvents`: Marks a transaction as `resolveAsSuccess` only if the sender address matches the
  message
  sender and there are no output events.

When a transaction is created, it must be registered in the system for later resolution. Use the
`StatusObserverServiceStub` to define the following parameters:

- `onAddress`: The account on which the transaction will execute.
- `queryId`: The query ID from the `StatusServiceStub`.
- A list of `StatusHandlers` that will execute when `onAddress` receives a message with `queryId`.

Use `StonfiV1Service#closeUserPosition` as a example.

## Summary of Integration Steps

At the end of the process, you need to:

1. Update `yield-spec.yaml` file.
2. Implement the `LoaderService` and `BlockchainRecordParser` classes.
3. Implement the `YourProtocolNameService` (e.g., `StonfiV1Service`), which creates transactions and returns up-to-date
   pool and user data.
4. Update the `YieldService` to support your new service.
5. Create handlers to monitor transactions using the `StatusHandler` class.

## FAQ

### We have no experience with Kotlin.

That's okay.
Kotlin is very similar to TypeScript and has a rich documentation: https://kotlinlang.org/docs/home.html.
Additionally, we have written an example of integration with Stone.fi V1 that you can refer to.
If you have any questions, please feel free to ask them here: https://t.me/swapcoffee_dev_chat.

### Does the code need to be compiled?

Yes, it does.

### Do we need to write tests?

Not necessarily, but we recommend that you launch your code and test it by making REST API requests.

### Where can I find answers to my questions?

You can ask any questions at: https://t.me/swapcoffee_dev_chat.