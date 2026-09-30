import { describe, test, afterAll, beforeAll } from 'vitest';
import { ToDoStatus, type ToDo } from './types.js';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { addDoc, deleteDoc, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';
import { setup, setupFirestore } from './setup.js';
import { myToDo, notMyToDo } from './test-data.js';

describe('Firestore rules tests', async () => {
  const env = await setup();

  const myOwnerId = myToDo.ownerId;
  const me = env.authenticatedContext(myOwnerId);
  const myDb = me.firestore();

  const collectionName = 'todos';
  const ownerIdAttributeName = 'ownerId';

  beforeAll(async () => {
    await setupFirestore(env);
  });
  
  afterAll(async () => {
    await env.clearFirestore();
    await env.cleanup();
  });

  test('Users can list their todos', async () => {
    await assertSucceeds(getDocs(
      myDb.collection(collectionName).where(ownerIdAttributeName, '==', myOwnerId)
    ));
  });

  test('Users can read one of their todo', async () => {
    await assertSucceeds(getDoc(doc(myDb, `${collectionName}/${myToDo.uid}`)));
  });

  test('Users can update their todo', async () => {
    const updatedToDo: ToDo = { ...myToDo, status: ToDoStatus.STARTED }
    await assertSucceeds(setDoc(doc(myDb, `${collectionName}/${myToDo.uid}`), updatedToDo));
  });

  test('Users can create another todo', async () => {
    const newToDo: ToDo = { ...myToDo, name: 'Another todo', uid: 'another uid' }
    await assertSucceeds(setDoc(doc(myDb, `${collectionName}/${newToDo.uid}`), newToDo));
  });

  test("Users cannot create a todo with another user's UID", async () => {
    const newToDo: ToDo = { ...myToDo, name: 'Another todo', uid: 'some other uid', ownerId: notMyToDo.ownerId }
    await assertFails(setDoc(doc(myDb, `${collectionName}/${newToDo.uid}`), newToDo))
  });

  test('Users cannot read all todos', async () => {
    await assertFails(getDocs(
      myDb.collection(collectionName)
    ));
  });

  test('Users cannot read a todo they are not owner of', async () => {
    await assertFails(getDoc(doc(myDb, `${collectionName}/${notMyToDo.uid}`)));
  });

  test('Users cannot update a todo they are not owner of', async () => {
    await assertFails(setDoc(doc(myDb, `${collectionName}/${notMyToDo.uid}`), myToDo));
  });

  test('Users cannot delete a todo they are not owner of', async () => {
    await assertFails(deleteDoc(doc(myDb, `${collectionName}/${notMyToDo.uid}`)));
  });

  test('Users can delete their own todo', async () => {
    await assertSucceeds(deleteDoc(doc(myDb, `${collectionName}/${myToDo.uid}`)));
  });

});
