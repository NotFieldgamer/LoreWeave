import { SignIn } from "@clerk/nextjs";
import { AuthShell } from "@/components/AuthShell";

export default function SignInPage() {
  return (
    <AuthShell title="Welcome back" subtitle="Step back into your adventure.">
      <SignIn routing="path" path="/sign-in" signUpUrl="/sign-up" forceRedirectUrl="/dashboard" />
    </AuthShell>
  );
}
