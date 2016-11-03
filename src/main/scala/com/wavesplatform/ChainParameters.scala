package com.wavesplatform

import scorex.account.{Account, AddressScheme}
import scorex.transaction.{GenesisTransaction, Transaction}
import com.wavesplatform.settings.Constants
import scorex.settings.WavesHardForkParameters

/**
  * ChainParameters contains the data needed for working with an instantiation of particular chain
  */
abstract class ChainParameters extends WavesHardForkParameters {
  val initialBalance: Long
  val genesisTimestamp: Long
  val genesisTxs : Seq[Transaction]
  val addressScheme: AddressScheme
}

object TestNetParams extends ChainParameters {
  val initialBalance = Constants.UnitsInWave * Constants.TotalWaves
  val genesisTimestamp = 1460952000000L
  val genesisTxs = {
    val txs = Seq(
      GenesisTransaction(new Account("3My3KZgFQ3CrVHgz6vGRt8687sH4oAA1qp8"), (initialBalance * 0.04).toLong, genesisTimestamp),
      GenesisTransaction(new Account("3NBVqYXrapgJP9atQccdBPAgJPwHDKkh6A8"), (initialBalance * 0.02).toLong, genesisTimestamp),
      GenesisTransaction(new Account("3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh"), (initialBalance * 0.02).toLong, genesisTimestamp),
      GenesisTransaction(new Account("3NCBMxgdghg4tUhEEffSXy11L6hUi6fcBpd"), (initialBalance * 0.02).toLong, genesisTimestamp),
      GenesisTransaction(new Account("3N18z4B8kyyQ96PhN5eyhCAbg4j49CgwZJx"), (initialBalance * 0.9).toLong, genesisTimestamp)
    )
    require(txs.foldLeft(0L)(_ + _.amount) == initialBalance)
    txs
  }
  override val addressScheme: AddressScheme = new AddressScheme {
    override val chainId: Byte = 'T'.toByte
  }

  override def allowTemporaryNegativeUntil: Long = 1477958400000L

  override def requireSortedTransactionsAfter: Long = 1477958400000L
}

object MainNetParams extends ChainParameters {
  val initialBalance = Constants.UnitsInWave * Constants.TotalWaves
  val genesisTimestamp = 1460952000000L
  val genesisTxs = {
    val txs = Seq(
      GenesisTransaction( new Account("3PAWwWa6GbwcJaFzwqXQN5KQm7H96Y7SHTQ"), initialBalance - 5 * Constants.UnitsInWave, genesisTimestamp),
      GenesisTransaction( new Account("3P8JdJGYc7vaLu4UXUZc1iRLdzrkGtdCyJM"), Constants.UnitsInWave, genesisTimestamp),
      GenesisTransaction( new Account("3PAGPDPqnGkyhcihyjMHe9v36Y4hkAh9yDy"), Constants.UnitsInWave, genesisTimestamp),
      GenesisTransaction( new Account("3P9o3ZYwtHkaU1KxsKkFjJqJKS3dLHLC9oF"), Constants.UnitsInWave, genesisTimestamp),
      GenesisTransaction( new Account("3PJaDyprvekvPXPuAtxrapacuDJopgJRaU3"), Constants.UnitsInWave, genesisTimestamp),
      GenesisTransaction( new Account("3PBWXDFUc86N2EQxKJmW8eFco65xTyMZx6J"), Constants.UnitsInWave, genesisTimestamp)
    )
    require(txs.foldLeft(0L)(_ + _.amount) == initialBalance)
    txs
  }
  override val addressScheme: AddressScheme = new AddressScheme {
    override val chainId: Byte = 'W'.toByte
  }
  override def allowTemporaryNegativeUntil: Long = 1478736000000L

  override def requireSortedTransactionsAfter: Long = 1478736000000L
}
