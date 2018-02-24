package com.humanity.cluster

import java.util.UUID

import akka.actor.Address

case class RestartGame()
case class Answer(content: String)
case class AnswerFail(reason: String)
case class Question(content: String)
case class QuestionFail(reason: String)
case class DeckQuestion(a: String)
case class DeckAnswer(count: Int)
case class Answers(obj: Map[UUID,Answer])
case class StartGameRound(deck: Address)
case class PlayersReady()
case class NoQuestionsLeft()
case class NoAnswersLeft()