import core._ 
import util._
import java.io._
import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.collection.mutable.HashMap
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession

class SlowAddressClosure (args:List[String]) extends AddressClosure (args){ 
 def adaptTreeIfNecessary(mapDSOA: HashMap[Hash, DisjointSetOfAddresses]): HashMap[Hash, DisjointSetOfAddresses] =
  {
    val timeStart = System.currentTimeMillis
    println("     Adapting tree to database ...")

    val query = "select hash, representant from addresses"
    // weird trick to allow slick using Array Bytes
    implicit val GetByteArr = GetResult(r => r.nextBytes())
    val q2 = Q.queryNA[(Array[Byte],Array[Byte])](query)

    for (pair <- q2)
    {
      val (hash, representant) = pair
      val address = Hash(hash)
      if (mapDSOA.contains(address))
      {
        mapDSOA(address).find.parent = Some(DisjointSetOfAddresses(Hash(representant)))
        mapDSOA remove address
      }
    }

    /*for ( (address, dsoa) <- mapDSOA)
    {
      // weird trick to allow slick using Array Bytes
      implicit val GetByteArr = GetResult(r => r.nextBytes())
      Q.queryNA[Array[Byte]]("select representant from addresses where hash= %s;" format (address)).list match
      {
        case representant::xs =>
          dsoa.find.parent = Some(DisjointSetOfAddresses(Hash(representant)))
          mapDSOA remove address
        case _  =>
      }
    }*/

    println("     Tree adapted in %s ms" format (System.currentTimeMillis - timeStart))

    mapDSOA
  }

def initializeAddressDatabaseFileIfNecessary = { }
  def createTablesIfNecessary = {  }
}
