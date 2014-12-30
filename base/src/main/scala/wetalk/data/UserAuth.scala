package wetalk.data

import wetalk.parser.{ResponseMessage, RequestMessage}

/**
 * Created by goldratio on 11/24/14.
 */
case class UserAuth(seqNo: String, userName: String, password: String) extends RequestMessage
case class UserSync(seqNo: String, userId: Int, syncKey: Long) extends RequestMessage

case class UserInfo(userName: String)

object NotFound

case class CheckRelationShip(userId: Int, friendId: Int)

case class CheckGroupRelationShip(userId: Int, groupId: Int)

case class GetDepartment(departmentId: Int)

case class GetFriend(userId: Int)

case class GetRecentContract(userId: Int)

case class GetGroupList(userId: Int)

case class GetRecentGroupList(userId: Int)

case class GetGroupInfo(groupId: Int)

case class CreateGroup(user: Int, members: List[Int])

case class OfflineMessage(userId: Int, message: ResponseMessage)

case class UserSearch(name: String, page: Int)