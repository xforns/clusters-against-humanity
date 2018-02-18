package com.humanity.cluster

case class Answer(content: String)
case class AnswerFail(reason: String)
case class Question(content: String)
case class QuestionFail(reason: String)
case class DeckQuestion(count: Int)
case class DeckAnswer(count: Int)
case class Questions(obj: Seq[Question])
case class Answers(obj: Seq[Answer])
