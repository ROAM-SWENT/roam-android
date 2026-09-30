import { ToDoStatus, type ToDo } from './types.js'

export const myToDo: ToDo = {
  uid: "1",
  name: "ToDo 1",
  description: "My ToDo",
  assigneeName: "me",
  dueDate: new Date(0),
  status: ToDoStatus.CREATED,
  ownerId: "owner-1"
}

export const notMyToDo: ToDo = {
  uid: "2",
  name: "ToDo 2",
  description: "Not my ToDo",
  assigneeName: "Not me",
  dueDate: new Date(0),
  status: ToDoStatus.ENDED,
  ownerId: "owner-2"
}
