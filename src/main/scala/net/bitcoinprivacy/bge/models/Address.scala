package net.bitcoinprivacy.bge.models

import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }
import scala.slick.jdbc.meta.MTable
import util.Hash
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.core.{Address => Add}
import org.bitcoinj.core.AddressFormatException
import core._

case class Address(address: String, balance: Long)
case class AddressesSummary(count: Int, sum: Long)

object Address extends core.BitcoinDB {

  def getWallet(hash: Array[Byte], from: Int, until: Int) = transactionDBSession {

    val hashList = addresses.filter(_.hash === hash).map(_.representant).firstOption match {
      case None => List(hash)
      case Some(a) => addresses.filter(_.representant === a).drop(from).take(until-from).map(_.hash).run.toList
    }

    balances.filter(_.address inSetBind(hashList)).map(p => (p.address, p.balance)).run map
    (p=> Address(hashToAddress(p._1),p._2))

  }

  def getAddressList[A <: Table[_] with BalanceField with HashField with BlockHeightField](richListTable: TableQuery[A], from: Int, until: Int): List[Address] = transactionDBSession {

    richListTable.sortBy(p => (p.block_height.desc,p.balance.desc)).
      drop(from).take(Math.min(1000, until-from)).map(p=> (p.hash,p.balance)).run.toList map
        (p => Address(hashToAddress(p._1), p._2))

  }

  def getWalletSummary(hash: Array[Byte]) = transactionDBSession {

    val representantOption = addresses.filter(_.hash === hash).map(_.representant).firstOption

    val (count, sum)  = representantOption match {

      case None =>

        (1, balances.filter(_.address === hash).map(_.balance).firstOption.getOrElse(0L))

      case Some(a) =>

        (addresses.filter(_.representant === a).size.run, closureBalances.filter(_.address === a).map(_.balance).firstOption.getOrElse(0L))

    }

    AddressesSummary(count, sum)

  }

  def getAddressListSummary[A <: Table[_] with BalanceField with HashField with BlockHeightField](richListTable: TableQuery[A]): AddressesSummary = transactionDBSession {

    val query = richListTable.sortBy(p => (p.block_height.desc,p.balance.desc))
      .take(1000).map(_.balance)

    AddressesSummary(query.length.run,query.sum.run.getOrElse(0L))

  }

  def hashToAddress(hash: Array[Byte]): String = hash.length match {

    case 20 => new Add(MainNetParams.get,0,hash).toString

    case 21 => new Add(MainNetParams.get,hash.head.toInt,hash.tail).toString

    case 0 => "No decodable address found"

    case x if (x%20==1) =>

      (for (i <- 1 to hash.length-20 by 20)
       yield hashToAddress(hash.slice(i,i+20)) ).mkString(",")

    case _  => hash.length + " undefined"
  }

}
