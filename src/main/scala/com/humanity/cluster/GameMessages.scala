package com.humanity.cluster

case class Answer(content: String)
case class AnswerFail(reason: String)
case class Question(content: String)
case class QuestionFail(reason: String)