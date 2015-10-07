package net.bitcoinprivacy.bge.models


import scala.slick.driver.PostgresDriver.simple._
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession
import scala.slick.jdbc.{ StaticQuery => Q }
import scala.slick.jdbc.meta.MTable
import util.Hash

case class UTXO(tx: String, value:Long)

object UTXO extends core.BitcoinDB
{
  def getUTXOs(address: Array[Byte], page: Int) =
    transactionDBSession{

      val utxos = for (b<- utxo.filter(_.address===address).drop((page-1)*1000).take(1000)) 
                       yield (b.transaction_hash ,b.value)

      utxos.run.toVector map (p => UTXO(Hash(p._1).toString, p._2))

    }

  def getUTXOsByTransaction(transactionHash: Array[Byte], page: Int) =
    transactionDBSession {

      val outputsFromUTXOS = for (b<-utxo.filter(_.transaction_hash===transactionHash))
                             yield (b.transaction_hash, b.value)

      

      outputsFromUTXOS.run.toVector map (p => UTXO(Hash(p._1).toString, p._2))


    }

}


