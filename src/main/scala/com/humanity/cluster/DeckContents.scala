package com.humanity.cluster

import java.util.UUID

object DeckContents {

  var questions = Map(
    UUID.randomUUID() -> Question("When all else fails, I can always masturbate to.. "),
    UUID.randomUUID() -> Question("An Oedipus complex. "),
    UUID.randomUUID() -> Question("Incest. "),
    UUID.randomUUID() -> Question("What would grandma find disturbing, yet oddly charming? "),
    UUID.randomUUID() -> Question("What brought the orgy to a grinding halt? "),
    UUID.randomUUID() -> Question("Daddy, why is mommy crying? "))

  var answers = Map(
    UUID.randomUUID() -> Answer("Grandpa's ashes"),
    UUID.randomUUID() -> Answer("Kid-tested, mother-approved"),
    UUID.randomUUID() -> Answer("High five, bro"),
    UUID.randomUUID() -> Answer("The biggest, blackest dick"),
    UUID.randomUUID() -> Answer("My worthless son"),
    UUID.randomUUID() -> Answer("Friction"))

}
