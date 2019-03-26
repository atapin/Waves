package com.wavesplatform.it.sync

import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.account.PrivateKeyAccount
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.it.NodeConfigs.Default
import com.wavesplatform.it._
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.api.SyncMatcherHttpApi._
import com.wavesplatform.it.api.{MatcherCommand, MatcherState}
import com.wavesplatform.it.sync.config.MatcherPriceAssetConfig._
import com.wavesplatform.matcher.AssetPairBuilder
import com.wavesplatform.matcher.model.OrderValidator
import com.wavesplatform.transaction.Asset.{IssuedAsset, Waves}
import com.wavesplatform.transaction.assets.{IssueTransaction, IssueTransactionV1}
import com.wavesplatform.transaction.assets.exchange.{AssetPair, Order, OrderType, OrderV1}
import org.scalacheck.Gen

import scala.concurrent.duration.DurationInt
import scala.util.Random

// Works only with kafka
class MultipleMatchersTestSuite extends MatcherSuiteBase {
  private def configOverrides = ConfigFactory.parseString("""waves.matcher {
      |  price-assets = ["WAVES"]
      |  snapshots-interval = 51
      |}""".stripMargin)

  private def matcher1NodeConfig = configOverrides.withFallback(Default.head)
  private def matcher2NodeConfig =
    ConfigFactory
      .parseString("""
      |waves {
      |  network.node-name = node11
      |  miner.miner.enable = no
      |}
      |akka.kafka.consumer.kafka-clients.group.id = 1""".stripMargin)
      .withFallback(matcher1NodeConfig)

  override protected def nodeConfigs: Seq[Config] = Seq(matcher1NodeConfig, matcher2NodeConfig)

  private def matcher1Node = nodes.head
  private def matcher2Node = nodes(1)

  private val issue1 = IssueTransactionV1
    .selfSigned(
      sender = alice,
      name = "Asset1".getBytes(),
      description = "Asset1 description".getBytes(),
      quantity = Long.MaxValue,
      decimals = 8,
      reissuable = false,
      fee = issueFee,
      timestamp = System.currentTimeMillis()
    )
    .explicitGet()

  private val issue2 = IssueTransactionV1
    .selfSigned(
      sender = alice,
      name = "Asset2".getBytes(),
      description = "Asset2 description".getBytes(),
      quantity = Long.MaxValue,
      decimals = 8,
      reissuable = false,
      fee = issueFee,
      timestamp = System.currentTimeMillis()
    )
    .explicitGet()

  private val assetPairs = {
    val assetPair1 = mkPair(issue1, issue2)
    val assetPair2 = AssetPair(assetPair1.amountAsset, Waves)
    val assetPair3 = AssetPair(assetPair1.priceAsset, Waves)

    Seq(assetPair1, assetPair2, assetPair3)
  }

  private val placesNumber = OrderValidator.MaxActiveOrders / assetPairs.size
  private val aliceOrders  = assetPairs.flatMap(mkOrders(alice, _, OrderType.SELL, placesNumber)).toVector
  private val bobOrders    = assetPairs.flatMap(mkOrders(bob, _, OrderType.BUY, placesNumber)).toVector

  private val orders = aliceOrders ++ bobOrders

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    // Issue assets by Alice
    val assetIds = {
      val txs = Seq(issue1, issue2).map(x => matcher1Node.broadcastRequest(x.json()).id -> x)
      txs.map { case (id, info) => nodes.waitForTransaction(id).id -> info }.toMap
    }

    // Share assets with Bob
    val xs = assetIds.map { case (id, info) => node.broadcastTransfer(alice, bob.address, info.quantity / 2, minFee, Some(id), None).id }
    xs.foreach(nodes.waitForTransaction)
  }

  "Place, fill and cancel a lot of orders" in {
    val alicePlaces = aliceOrders.map(MatcherCommand.Place(matcher1Node, _))
    val bobPlaces   = bobOrders.map(MatcherCommand.Place(matcher2Node, _))
    val places      = Random.shuffle(alicePlaces ++ bobPlaces)
    executeCommands(places)
  }

  "Wait until all requests are processed" in {
    for {
      o    <- orders
      node <- nodes
    } node.waitOrderStatus(o.assetPair, o.idStr(), "Filled")
  }

  "States on both matcher should be equal" in {
    val state1 = state(matcher1Node)
    val state2 = state(matcher2Node)
    state1 shouldBe state2
  }

  private def mkOrders(account: PrivateKeyAccount, pair: AssetPair, tpe: OrderType, number: Int): Vector[Order] =
    (1 to number).map { i =>
      OrderV1(
        sender = account,
        matcher = matcher,
        pair = pair,
        orderType = tpe,
        amount = 100000L,
        price = 80000L,
        timestamp = System.currentTimeMillis() + i,
        expiration = System.currentTimeMillis() + 1.day.toMillis,
        matcherFee = matcherFee
      )
    }.toVector

  private def state(node: Node) = clean(node.matcherState(assetPairs, orders, Seq(alice, bob)))

  // Because we can't guarantee that SaveSnapshot message will come at same place in a orderbook's queue on both matchers
  private def clean(state: MatcherState): MatcherState = state.copy(
    snapshots = state.snapshots.map { case (k, _) => k -> 0L }
  )

  private def mkPair(issue1: IssueTransaction, issue2: IssueTransaction): AssetPair = {
    val p = AssetPair(IssuedAsset(issue1.assetId()), IssuedAsset(issue2.assetId()))
    val x = AssetPairBuilder.assetIdOrdering.compare(Some(issue1.assetId()), Some(issue2.assetId()))
    if (x > 0) p else p.reverse
  }
}
