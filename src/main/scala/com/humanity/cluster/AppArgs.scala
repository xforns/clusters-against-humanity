package com.humanity.cluster

object AppArgs {

  @throws(classOf[IllegalArgumentException])
  def systemName():String = {
    sys.props.getOrElse(
        "actorSystemName",
        throw new IllegalArgumentException("Actor system name must be defined by the actorSystemName property")
      )
  }

}
