export type EntityDto = { id: string; name: string; type: string; summary: string };
export type RelationDto = { from: string; type: string; to: string };
export type FactDto = { id: string; text: string; turn: number; status: string };
export type WorldState = { entities: EntityDto[]; relations: RelationDto[]; facts: FactDto[] };
export type SessionDto = { id: string; title: string; genre: string; createdAt: string; turnCount: number };
export type TurnDto = { index: number; action: string; narration: string };
export type SessionDetail = {
  id: string; title: string; genre: string; openingScene: string; turnCount: number; turns: TurnDto[];
};
export type TurnDelta = { turn: number; newFacts: string[]; superseded: string[] };
export type StorySegment = { role: "gm" | "player"; text: string };

// Dashboard (M5)
export type AdventureStat = {
  id: string; title: string; genre: string; turnCount: number; entityCount: number; factCount: number;
};
export type RecentFact = { sessionId: string; title: string; text: string; turn: number };
export type Dashboard = {
  worlds: number; turns: number; entities: number; facts: number;
  adventures: AdventureStat[]; recent: RecentFact[];
};
