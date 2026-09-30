export interface ToDo {
  uid: string
  name: string
  description: string,
  assigneeName: string,
  dueDate: Date,
  location?: Location,
  status: ToDoStatus,
  ownerId: string
}

interface Location {
  latitude: number,
  longitude: number,
  name: string
}

export enum ToDoStatus {
  CREATED,
  STARTED,
  ENDED,
  ARCHIVED
}
