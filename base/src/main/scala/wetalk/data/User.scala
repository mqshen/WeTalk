package wetalk.data

import java.util.Date

/**
 * Created by goldratio on 11/3/14.
 */
case class User(id:Int, name: String, nick: String, avatar: String, address: String, status: Int,
                sex: Int, userType: Int,  phone: String, mail: String, created: Date, updated: Date ) {

}

case class Group(id: Int, name: String, avatar: String, description: String, creator: Int, groupType: Int, status: Int, count: Int,
                  created: Date, updated: Date)
case class Department(id: Int, title: String, description: String, parentID: String,
                      leader: String, status: Int, created: Date, updated: Date)

case class RecentContact(id: Int, userId: Int, friendId: Int, status: Int, created: Date, updated: Date)