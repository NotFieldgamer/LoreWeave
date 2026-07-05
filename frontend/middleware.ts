import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

// Everything under the (app) route group is private.
const isProtected = createRouteMatcher(["/dashboard(.*)", "/play(.*)"]);

export default clerkMiddleware(async (auth, req) => {
  if (isProtected(req)) await auth.protect();
});

export const config = {
  matcher: ["/((?!_next|.*\\..*).*)", "/(api|trpc)(.*)"],
};
