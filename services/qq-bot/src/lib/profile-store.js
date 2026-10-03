import fs from "node:fs";
import path from "node:path";
import { projectRoot } from "./env.js";

const dataDir = path.join(projectRoot, "data", "channel-runtime", "qq");
const dataFile = path.join(dataDir, "profiles.json");

function defaultProfile() {
  return {
    otherAlias: "对方",
    relationshipStage: "未提供",
    goal: "自动判断",
    userStyle: "短句、自然、不油腻",
  };
}

export class ProfileStore {
  constructor(filePath = dataFile) {
    this.filePath = filePath;
    this.data = {};
    this.load();
  }

  load() {
    try {
      if (fs.existsSync(this.filePath)) {
        this.data = JSON.parse(fs.readFileSync(this.filePath, "utf8"));
      }
    } catch {
      this.data = {};
    }
  }
  get(userId) {
    return {
      ...defaultProfile(),
      ...(this.data[userId] || {}),
    };
  }

  set(userId, patch) {
    this.data[userId] = {
      ...this.get(userId),
      ...patch,
      updatedAt: new Date().toISOString(),
    };
    this.save();
    return this.get(userId);
  }

  clear(userId) {
    delete this.data[userId];
    this.save();
    return defaultProfile();
  }

  save() {
    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    const tmp = this.filePath + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(this.data, null, 2), "utf8");
    fs.renameSync(tmp, this.filePath);
  }
}

export { defaultProfile };
