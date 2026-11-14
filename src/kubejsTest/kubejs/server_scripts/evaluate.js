EvaluateJS.registerEvaluator('evaluate_tests:params', (context, params) => {
    context.set(params.item, params.value)
    params.value = 999999 // Each invocation receives its own params copy.
})

EvaluateJS.registerEvaluator('evaluate_tests:removed', (context, params) => {
    context.addValue('minecraft:structure_void', 7.75)
})

EvaluateJS.registerEvaluator('evaluate_tests:unused', () => {
    throw new Error('Uninstantiated JS evaluator must not run')
})

EvaluateEvents.values(event => {
    event.set('minecraft:amethyst_shard', 17.25)
    event.set('minecraft:amethyst_shard', 18.25)
    event.set(['minecraft:heart_of_the_sea', '#evaluate_tests:script_values'], 21.5)
    event.override('minecraft:trial_spawner', 999.5)
    event.addEvaluator('evaluate_tests:raw', {
        type: 'evaluate:constant', item: 'minecraft:pink_petals', value: 111.25, priority: 1000
    })
    event.evaluator('evaluate_tests:inline', (context, params) => {
        context.set('minecraft:reinforced_deepslate', 77.25)
        context.addValue('minecraft:trial_spawner', 10.5)
        context.conversion('2x minecraft:debug_stick', ['minecraft:trial_spawner', 'minecraft:amethyst_shard'], 1.25)
    })
    event.js('evaluate_tests:generated', 'evaluate_tests:params', { item: 'minecraft:dragon_egg', value: 5000.25 })

    for (const value of [-1, NaN, Infinity]) {
        let rejected = false
        try { event.set('minecraft:barrier', value) } catch (error) { rejected = true }
        if (!rejected) throw new Error('Invalid script value was accepted: ' + value)
    }
})
