package recipes

import "testing"

func TestParse(t *testing.T) {
	good := `{"recipes":[{"title":"Arroz de forno","summary":"","minutes":40,"servings":4,"ingredients":[{"name":"arroz","amount":"2 xícaras","fromStock":true}],"steps":["cozinhe"]},{"title":"","ingredients":[],"steps":[]}]}`
	for _, content := range []string{good, "```json\n" + good + "\n```", "Claro! Aqui estão:\n" + good} {
		r, err := parse(content)
		if err != nil || len(r) != 1 || r[0].Title != "Arroz de forno" || r[0].Missing == nil {
			t.Fatalf("%q: %+v %v", content, r, err)
		}
	}
	for _, content := range []string{"", "não sei", `{"recipes":[]}`, `{"recipes":[{"title":"x"}]}`, `{"recipes":`} {
		if _, err := parse(content); err == nil {
			t.Fatalf("%q should fail", content)
		}
	}
}
