const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.onAdminCreateSetAdminClaim = functions.firestore
  .document('admin/{uid}')
  .onCreate(async (snap, context) => {
    const uid = context.params.uid;
    try {
      await admin.auth().setCustomUserClaims(uid, { admin: true });
    } catch (err) {
      console.error(err);
    }
    return null;
  });

exports.onAdminDeleteRemoveAdminClaim = functions.firestore
  .document('admin/{uid}')
  .onDelete(async (snap, context) => {
    const uid = context.params.uid;
    try {
      await admin.auth().setCustomUserClaims(uid, { admin: false });
    } catch (err) {
      console.error(err);
    }
    return null;
  });