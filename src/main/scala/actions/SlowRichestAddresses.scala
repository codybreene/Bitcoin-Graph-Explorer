package actions

import util._
import scala.slick.jdbc.{StaticQuery => Q}
//import scala.slick.driver.SQLiteDriver.simple._
import scala.slick.jdbc.JdbcBackend.Database.dynamicSession

/**
 * Created by yzark on 22.12.14.
 */
object SlowRichestAddresses {


  /**
   * Created by yzark on 15.12.14.
   */
  transactionDBSession {
    Q.updateNA( """
       insert
        into richest_addresses
       select
        (select max(block_height) from blocks) as block_height,
        hash,
        balance
      from
        addresses
      order by
        balance desc
      limit 100
    ;""").execute
    Q.updateNA("create index if not exists richest1 on richest_addresses(block_height);").execute
  }
}