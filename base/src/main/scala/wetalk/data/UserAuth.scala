package wetalk.data

import wetalk.parser.{ResponseMessage, RequestMessage}

/**
 * Created by goldratio on 11/24/14.
 */
case class UserAuth(seqNo: String, userName: String, password: String) extends RequestMessage

object UserAuthSuccess

case class UserTokenAuth(seqNo: String, account: String, token: String) extends RequestMessage

case class UserSync(seqNo: String, userId: String, syncKey: Long) extends RequestMessage

case class UserInfo(userName: String)

case class UserToken(account: String, token: String)

object NotFound

case class CheckRelationShip(userId: Int, friendId: Int)

case class CheckGroupRelationShip(userId: Int, groupId: Int)

case class GetDepartment(departmentId: Int)

case class GetUser(userId: Int)

case class GetFriend(userId: String)

case class GetRecentContract(userId: Int)

case class GetGroupList(userId: String)

case class GetRecentGroupList(userId: String)

case class GetGroupInfo(groupId: String)

case class CreateGroup(user: Int, members: List[Int])

case class OfflineMessage(userId: String, message: ResponseMessage)

case class UserSearch(name: String, page: Int)