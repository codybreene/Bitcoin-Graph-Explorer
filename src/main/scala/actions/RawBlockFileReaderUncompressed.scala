package actions

/**
 * Created with IntelliJ IDEA.
 * User: stefan 
 * Date: 10/29/13
 * Time: 9:58 PM
 * To change this template use File | Settings | File Templates.
 */

import libs._

import com.google.bitcoin.core._
import com.google.bitcoin.params.MainNetParams
import com.google.bitcoin.store.SPVBlockStore
import com.google.bitcoin.utils.BlockFileLoader
import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession
import scala.collection.mutable.HashMap
import scala.slick.jdbc.meta.MTable
import scala.collection.JavaConversions._
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.slick.driver.MySQLDriver.simple._
import Database.threadLocalSession

class RawBlockFileReaderUncompressed(args:List[String]){
  val params = MainNetParams.get();
  val loader = new BlockFileLoader(params,BlockFileLoader.getReferenceClientBlockFileList());
  var counter = 0
  var totalOutIn = 0
  var listData:List[String] = Nil
  val saveInterval = 100000
  var blockCount = 0
  var nrBlocksToSave = if (args.length > 0) args(0).toInt else 1000

  databaseSession {

    if (args.length > 1 && args(1) == "init" )
      initializeDB
    else
      blockCount = Query(RawBlocks.length).first


    val totalTime = doSomethingBeautiful
    println("Total time to save movements = " + totalTime + " ms")
    println("Total of movements = " + totalOutIn)
    println("Time required pro movement = " + totalTime.toDouble/totalOutIn +" ms")
    println("Wir sind sehr geil!")
  }

  def hex2Bytes(hex: String): Array[Byte] = {
    (for {i <- 0 to hex.length - 1 by 2 if i > 0 || !hex.startsWith("0x")}
      yield hex.substring(i, i + 2))
        .map(Integer.parseInt(_, 16).toByte).toArray
  }
  def initializeDB: Unit =
  {
    println("Resetting tables of the bitcoin database.")
    var tableList = MTable.getTables.list;
    var tableMap = tableList.map{t => (t.name.name, t)}.toMap;
    if (tableMap.contains("outputs"))
      (RawOutputs.ddl).drop
    (RawOutputs.ddl).create
    if (tableMap.contains("inputs"))
      (RawInputs.ddl).drop
    (RawInputs.ddl).create
    if (tableMap.contains("blocks"))
      (RawBlocks.ddl).drop
    (RawBlocks.ddl).create
    if (tableMap.contains("grouped_addresses"))
      (GroupedAddresses.ddl).drop
    (GroupedAddresses.ddl).create
  }

  def saveDataToDB: Unit =
  {
    val startTime = System.currentTimeMillis
    println("Saving until block nr. " + blockCount + " ...")

    for (line <- listData)
      (Q.u + line+";").execute

    listData = Nil
    counter = 0
    val totalTime = System.currentTimeMillis - startTime
    println("Saved in " + totalTime + "ms")
  }

  def doSomethingBeautiful: Long =
  {
    println("Start")
    println("Reading binaries")
    var savedBlocksSet:Set[String] = Set.empty
    val savedBlocks =
      (for (b <- RawBlocks /* if b.id === 42*/)
        yield (b.hash))
    for (c <- savedBlocks)
      savedBlocksSet = savedBlocksSet + c

    nrBlocksToSave += blockCount

    println("Saving blocks from " + blockCount + " to " + nrBlocksToSave)
    val globalTime = System.currentTimeMillis
    for(
      block <- asScalaIterator(loader)
      if (!savedBlocksSet.contains(block.getHashAsString())))
    {
      val blockHash = block.getHashAsString()
      savedBlocksSet += blockHash

      if (counter > saveInterval || blockCount >= nrBlocksToSave )
      {
        saveDataToDB

        if (blockCount >= nrBlocksToSave)
          return System.currentTimeMillis - globalTime

      }
      blockCount += 1
      listData = "insert into blocks VALUES (" + '"' + blockHash + '"' + ")"::listData

      for (trans <- block.getTransactions.par)
      {
        val transactionHash = trans.getHashAsString()

        if (!trans.isCoinBase)
        {
          for (input <- trans.getInputs)
          {
            val outpointTransactionHash = input.getOutpoint.getHash.toString
            val outpointIndex = input.getOutpoint.getIndex.toInt
            listData = "insert into inputs VALUES (" + '"' + outpointTransactionHash + '"' + "," + outpointIndex +"," + '"' + transactionHash+'"'+")"::listData
            counter+=1
            totalOutIn+=1
          }
        }
        var index = 0

        for (output <- trans.getOutputs)
        {
          val addressHash:String =
            try
            {
              output.getScriptPubKey.getToAddress(params).toString
            }
            catch
            {
              case e: ScriptException =>
                val script = output.getScriptPubKey.toString
                if (script.startsWith("[65]"))
                {
                  val pubkeystring = script.substring(4, 134)
                  import Utils._
                  val pubkey = hex2Bytes(pubkeystring)
                  val address = new Address(params, sha256hash160(pubkey))
                  address.toString
                }
                else
                { // special case because bitcoinJ doesn't support pay-to-IP scripts
                  "0"
                }
            }
          val value = output.getValue.doubleValue
          listData = "insert into outputs VALUES (" + '"' + transactionHash + '"' + "," + '"'+addressHash + '"' + "," + index + "," + value + ")"::listData
          counter+=1
          totalOutIn+=1
          index+=1
        }
      }
    }
    return 0.toLong
  }
}
