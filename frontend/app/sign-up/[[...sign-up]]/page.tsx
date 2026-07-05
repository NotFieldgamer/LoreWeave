import { SignUp } from "@clerk/nextjs";
import { AuthShell } from "@/components/AuthShell";

export default function SignUpPage() {
  return (
    <AuthShell
      title="Begin your first tale"
      subtitle="Create an account and weave a world."
      altPrompt="Already have an account?"
      altLabel="Sign in"
      altHref="/sign-in"
    >
      <SignUp routing="path" path="/sign-up" signInUrl="/sign-in" forceRedirectUrl="/dashboard" />
    </AuthShell>
  );
}
