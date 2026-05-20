export function createDmApi({ request, rawRequest, baseUrl }) {
  const call = (path, init = {}) => request(path, init);
  return {
    request(path, init = {}) {
      return call(path, init);
    },
    loginWithFirebaseToken(firebaseIdToken) {
      return rawRequest(`${baseUrl}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firebaseIdToken })
      });
    },
    getAvailability() {
      return call("/api/users/me/availability");
    },
    patchAvailability(availabilityStatus) {
      return call("/api/users/me/availability", {
        method: "PATCH",
        body: JSON.stringify({ availabilityStatus })
      });
    },
    getMissingPets() {
      return call("/api/missing-pets?page=0&size=50&region=*");
    },
    getOwnMissingPets() {
      return call("/api/missing-pets?page=0&size=50&mineOnly=true");
    },
    getRooms() {
      return call("/api/chat/rooms");
    }
  };
}
